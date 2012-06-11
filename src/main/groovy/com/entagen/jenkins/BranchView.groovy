package com.entagen.jenkins

class BranchView {
    String templateJobPrefix
    String branchName

    public String getViewName() {
        return "$templateJobPrefix-$safeBranchName"
    }

    public String getSafeBranchName() {
        return branchName.replaceAll('/', '_')
    }


    public String toString() {
        return this.viewName
    }
}
