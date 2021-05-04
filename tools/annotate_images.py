#!/usr/bin/env python3

import os
import sys
import re
import subprocess

# Named groups could be helpful at some point in the future?
ternary_imagename_pat = r'\${\s*remote_img\s*\?\s*"(?P<remote_img>[^"]*)"\s*:\s*"(?P<local_img>[^"]*)"\s*}'
single_imagename_pat = r'(?P<img>[^"]*)'
imagename_pat = r'image\s*=\s*"(?:{0}|{1})"'.format(ternary_imagename_pat, single_imagename_pat)


def get_digest(templatefile, imagename, image_available):
    # This method requires downloading the image, which isn't ideal, but in most (?)
    # cases the machine running the bazel rule will already have the image around (CI?).  In either
    # case, it doesn't seem possible to reliably get the correct manifest digest otherwise,
    # since it's the hash of a json file and the docker hub API does something to the json
    # that alters the hash outcome.  Argh!

    if not image_available:
        #print("[TRACE] Pulling %s" % imagename)
        proc = subprocess.Popen(["docker", "pull", imagename], stdout=subprocess.PIPE).stdout.read()
        if not proc.splitlines(keepends=False):
            print("[ERROR] No output from docker for " + imagename + " on templatefile " + templatefile)
            return None
        #else:
            #print(["[TRACE] " + str(x) for x in proc.splitlines(keepends=False)])

    shortname = imagename.split(':')[0]  # Looking for image name with version tag suppresses digest, for some reason?
    cmd = ['docker', 'images', '--digests', '--filter', 'reference=%s' % shortname, "--format", "{{.Digest}}"]
    digest_output = subprocess.Popen(cmd, stdout=subprocess.PIPE).stdout.read().decode('utf-8').strip("' \n")
    digstr = digest_output.strip("\n")
    if digstr and "<none>" not in digstr:
        res = shortname + "@" + digstr # should be the full docker image name without tag with sha256 version digest
        return res


def get_available_images():
    lines = subprocess.Popen(["docker", "images", "--format", "{{.Repository}}"], stdout=subprocess.PIPE).stdout.read().decode('utf-8').split("\n")
    return [x for x in lines if x != "<none>"]


# Key reason docker wouldn't be available is if the build is happening in dazel.
def docker_is_available():
    try:
        out = subprocess.Popen(["docker", "--version"], stdout=subprocess.DEVNULL).wait()
        return bool(out)
    except FileNotFoundError:
        print("[ERROR] Not getting docker image hashes; docker CLI not found.")
        return False


def annotate_images(templatefile, outputfile, dot_git_dir):
    text = open(templatefile).read()
    if docker_is_available() and not os.environ.get("CI_EC2_INSTANCE_SIZE"):
        images = get_available_images()
        imagenames = [item for sublist in re.findall(imagename_pat, text) for item in sublist if item]
        for imagename in imagenames:
            if 'radix-labs' not in imagename:
                image_local = imagename.split(':')[0] in images
                image_wdigest = get_digest(templatefile, imagename, image_local)
                if image_wdigest:
                    text = text.replace(imagename, image_wdigest)
                    #print("[TRACE] Found and replaced version digest for " + imagename + " with " + image_wdigest)
                elif imagename[:len("bazel")] == "bazel":
                    pass
                    #print("[TRACE] skipping digesting of local build target %s" % imagename)
                else:
                    print("[WARN] Could not find version digest for %s" % imagename)

    git_head_file = os.path.join(dot_git_dir, "HEAD")
    branch_name = open(git_head_file, 'r').read().strip()
    if "ref: refs/heads" not in branch_name:
        # Then branch_name is probably a commit hash (this happens in CI)
        hash = branch_name
        branches = subprocess.Popen(["git", "--git-dir", dot_git_dir, "branch", "--contains", branch_name],
                                    stdout=subprocess.PIPE).stdout.read().decode('utf-8').strip().split('\n')
        branches = [x for x in branches if 'HEAD' not in x]
        branch_name = branches[0].strip(' *') if branches else ''
        # print("[TRACE] %s: git reports hash %s comes from branch %s (out of (%s))" %
        #       (os.path.basename(templatefile), hash, branch_name, branches))
    else:
        branch_name = branch_name.replace('ref: refs/heads/', '')

    branch_name = branch_name.replace('/', '-')  # Formatting to match workspace-status.sh

    if not branch_name:
        print("[WARN] %s: Failed to find valid branch name in build repo, using 'master' (.git/HEAD contains '%s')" %
              (os.path.basename(templatefile), branch_name))

        newline_indices = [m.end() - 2 for m in re.finditer(r"{CURRENT_BRANCH}.*\n", text)]
        diff = 0
        comment = "#Branch 'master' chosen due to failure to get current branch name during build.\n"

        text = text.replace('{CURRENT_BRANCH}"', 'master"')

        for index in newline_indices:
            # need to insert on a newline so it doesn't get inserted in the middle of a ternary statement
            text = text[:index + diff] + comment + text[index + diff:]
            diff += len(comment)
    else:
        text = text.replace("{CURRENT_BRANCH}", branch_name)

    with open(outputfile, 'w') as out:
        # print(text) this will print terraform template HCL files if you uncomment it
        out.write(text)
    # print("[TRACE] Done on %s" % templatefile)


if __name__ == '__main__':
    annotate_images(sys.argv[1], sys.argv[2], sys.argv[3])
