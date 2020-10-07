import os
import sys
import re
import subprocess

imagename_pat = r'image = "([^"]*)"'


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
        return "@".join([shortname, digstr])
    else:
        return ""


def get_available_images():
    lines = subprocess.Popen(["docker", "images"], stdout = subprocess.PIPE).stdout.read().decode('utf-8').split("\n")[1:]
    return [x.split()[0] for x in lines if x]


def annotate_images(templatefile, outputfile, dot_git_dir):
    if not os.environ.get("CI_EC2_INSTANCE_SIZE"):
        images = get_available_images()
        text = open(templatefile).read()
        imagenames = re.findall(imagename_pat, text)
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
        text = text.replace('{CURRENT_BRANCH}"', 'master"\t#Branch "master" chosen due to failure to get current branch name during build.')
    else:

        text = text.replace("{CURRENT_BRANCH}", branch_name)

    with open(outputfile, 'w') as out:
        out.write(text)
    print("Done on %s" % templatefile)

if __name__ == '__main__':
    annotate_images(sys.argv[1], sys.argv[2], sys.argv[3])
