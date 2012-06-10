package com.entagen.jenkins

class BranchView {
    String templateJobPrefix
    String branchName

    public String getViewName() {
        return "$templateJobPrefix-$branchName"
    }

    public String toString() {
        return this.viewName
    }
}
