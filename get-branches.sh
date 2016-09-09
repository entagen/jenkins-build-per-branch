#!/bin/bash
git clone $1 repo
cd repo
for branch in `git branch -r | grep -v HEAD`;do echo `git show --format="%ct" $branch | head -n 1` \\t$branch; done | sort -r
cd ../
rm -rf repo