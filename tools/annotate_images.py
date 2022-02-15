#!/usr/bin/env python3

import os
import sys
import re
import subprocess

# Named groups could be helpful at some point in the future?
ternary_imagename_pat = (
    r'\${\s*remote_img\s*\?\s*"(?P<remote_img>[^"]*)"\s*:\s*"(?P<local_img>[^"]*)"\s*}'
)
single_imagename_pat = r'(?P<img>[^"]*)'
imagename_pat = r'source\s*=\s*"(?:{0}|{1})"'.format(
    ternary_imagename_pat, single_imagename_pat
)


def get_commit_hash():
    return (
        subprocess.Popen(
            ["git", "rev-parse", "--short", "HEAD"],
            stdout=subprocess.PIPE,
        )
        .stdout.read()
        .decode("utf-8")
        .strip()
    )


def annotate_images(templatefile, outputfile, dot_git_dir):
    text = open(templatefile).read()
    imagenames = [
        item
        for sublist in re.findall(imagename_pat, text)
        for item in sublist
        if item
    ]
    commit_hash = get_commit_hash()

    for imagename in imagenames:
        image_wdigest = imagename.replace(".jar", "_" + commit_hash + ".jar")
        if image_wdigest:
            text = text.replace(imagename, image_wdigest)
            # print("[TRACE] Found and replaced version digest for " + imagename + " with " + image_wdigest)
        else:
            print("[WARN] Could not find version digest for %s" % imagename)

    with open(outputfile, "w") as out:
        # print(text) this will print terraform template HCL files if you uncomment it
        out.write(text)
    # print("[TRACE] Done on %s" % templatefile)


if __name__ == "__main__":
    annotate_images(sys.argv[1], sys.argv[2], sys.argv[3])
