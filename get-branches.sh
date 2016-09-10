#!/bin/bash

if [ ! -d $2 ]; then
    git clone $1
fi
cd $2
git fetch -p
for branch in `git branch -r | grep -v HEAD`;do echo `git show --format="%ct" $branch | head -n 1` \\t$branch; done | sort -r
