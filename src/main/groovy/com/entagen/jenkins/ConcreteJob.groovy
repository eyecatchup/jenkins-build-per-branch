package com.entagen.jenkins

class ConcreteJob {
    TemplateJob templateJob
    String jobName
    String branchName

    public String getSafeBranchName() {
        return branchName.replaceAll('/', '_')
    }
}
