package com.entagen.jenkins

import java.util.regex.Pattern

abstract class BranchSource {

    Pattern branchNameFilter = null

    public abstract List<ConcreteJob> getBranchJobs(List<TemplateJob> template)

    public Boolean passesFilter(String branchName) {
        if (!branchName) return false
        if (!branchNameFilter) return true
        return branchName ==~ branchNameFilter
    }
}
