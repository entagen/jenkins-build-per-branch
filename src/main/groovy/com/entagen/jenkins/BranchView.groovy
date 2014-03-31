package com.entagen.jenkins

class BranchView {
    String templateJobPrefix
    String branchName

    public String getViewName() {
		if(templateJobPrefix) {
	        return "$templateJobPrefix-$safeBranchName"
		}
		else {
			return safeBranchName
		}
    }

    public String getSafeBranchName() {
        return branchName.replaceAll('/', '_')
    }


    public String toString() {
        return this.viewName
    }
}
