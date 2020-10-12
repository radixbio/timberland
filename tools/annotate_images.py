#!/usr/bin/env python3

import os
import sys
import re
import subprocess

# Named groups could be helpful at some point in the future?
ternary_imagename_pat = r'\${\s*remote_img\s*\?\s*"(?P<remote_img>[^"]*)"\s*:\s*"(?P<local_img>[^"]*)"\s*}'
single_imagename_pat = r'(?P<img>[^"]*)'
imagename_pat = r'image\s*=\s*"(?:{0}|{1})"'.format(ternary_imagename_pat, single_imagename_pat)


def get_digest(imagename, image_available):
    # This method requires downloading the image, which isn't ideal, but in most (?)
    # cases the machine running the bazel rule will already have the image around (CI?).  In either
    # case, it doesn't seem possible to reliably get the correct manifest digest otherwise,
    # since it's the hash of a json file and the docker hub API does something to the json
    # that alters the hash outcome.  Argh!

    if not image_available:
        print("Pulling %s" % imagename)
        subprocess.Popen(["docker", "pull", imagename])

    shortname = imagename.split(':')[0] # Looking for image name with version tag suppresses digest, for some reason?
    cmd = ['docker', 'images', '--digests', '--filter', 'reference=%s' % shortname]
    digest_output = subprocess.Popen(cmd, stdout = subprocess.PIPE).stdout.read().decode('utf-8').strip("' \n")

    if "\n" in digest_output:
        digstr = digest_output.split('\n')[1].split()[2]
        if digstr and "<none>" not in digstr:
          return "@".join([shortname, digstr])
        else:
          return ""
    else:
        return ""


def get_available_images():
    lines = subprocess.Popen(["docker", "images"], stdout = subprocess.PIPE).stdout.read().decode('utf-8').split("\n")[1:]
    return [x.split()[0] for x in lines if x]


def annotate_images(templatefile, outputfile, dot_git_dir):
    if not os.environ.get("CI_EC2_INSTANCE_SIZE"):
        images = get_available_images()
        text = open(templatefile).read()
        imagenames = [item for sublist in re.findall(imagename_pat, text) for item in sublist if item]
        print("imagenames", imagenames)
        for imagename in imagenames:
            if 'radix-labs' not in imagename:
                image_local = imagename.split(':')[0] in images
                image_wdigest = get_digest(imagename, image_local)
                if image_wdigest:
                    text = text.replace(imagename, image_wdigest)
                else:
                    print("Could not find version digest for %s" % imagename)

    git_head_file = os.path.join(dot_git_dir, "HEAD")
    branch_name = open(git_head_file, 'r').read().strip()
    if "ref: refs/heads" not in branch_name:
        # Then branch_name is probably a commit hash (this happens in CI)
        hash = branch_name
        branches = subprocess.Popen(["git", "--git-dir", dot_git_dir, "branch", "--contains", branch_name],
         stdout = subprocess.PIPE).stdout.read().decode('utf-8').strip().split('\n')
        branches = [x for x in branches if 'HEAD' not in x]
        branch_name = branches[0].strip(' *') if branches else ''
        print("%s: git reports hash %s comes from branch %s (out of (%s))" %
            (os.path.basename(templatefile), hash, branch_name, branches))
    else:
        branch_name = branch_name.replace('ref: refs/heads/', '')

    branch_name = branch_name.replace('/', '-') # Formatting to match workspace-status.sh

    if not branch_name:
        print("%s: Failed to find valid branch name in build repo, using 'master' (.git/HEAD contains '%s')" %
         (os.path.basename(templatefile), branch_name))

        newline_indices = [m.end()-2 for m in re.finditer(r"{CURRENT_BRANCH}.*\n", text)]

        text = text.replace('{CURRENT_BRANCH}"', 'master"')

        for index in newline_indices:
          # need to insert on a newline so it doesn't get inserted in the middle of a ternary statement
          text = text[:index] + "#Branch 'master' chosen due to failure to get current branch name during build.\n" + text[index:]
    else:
        text = text.replace("{CURRENT_BRANCH}", branch_name)

    with open(outputfile, 'w') as out:
        out.write(text)
    print("Done on %s" % templatefile)

if __name__ == '__main__':
    annotate_images(sys.argv[1], sys.argv[2], sys.argv[3])
