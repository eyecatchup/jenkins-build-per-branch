package com.entagen.jenkins

class JenkinsJobManager {
    String templateJobPrefix
    String templateBranchName
    String gitUrl
    String nestedView
    String jenkinsUrl
    String branchNameRegex
    String viewRegex
    String jenkinsUser
    String jenkinsPassword

    Boolean dryRun = false
    Boolean noViews = false
    Boolean noDelete = false
    Boolean startOnCreate = false
    Boolean startExpected = false

    JenkinsApi jenkinsApi
    GitApi gitApi

    JenkinsJobManager(Map props) {
        for (property in props) {
            this."${property.key}" = property.value
        }
        initJenkinsApi()
        initGitApi()
    }

    void syncWithRepo() {
        List<String> allBranchNames = gitApi.branchNames
        List<String> allJobNames = jenkinsApi.jobNames

        // ensure that there is at least one job matching the template pattern, collect the set of template jobs
        List<TemplateJob> templateJobs = findRequiredTemplateJobs(allJobNames)

        // create any missing template jobs and delete any jobs matching the template patterns that no longer have branches
        syncJobs(allBranchNames, allJobNames, templateJobs)
    }

    public void syncJobs(List<String> allBranchNames, List<String> allJobNames, List<TemplateJob> templateJobs) {
        List<String> currentTemplateDrivenJobNames = templateDrivenJobNames(templateJobs, allJobNames)
        List<String> nonTemplateBranchNames = allBranchNames - templateBranchName
        List<ConcreteJob> expectedJobs = this.expectedJobs(templateJobs, nonTemplateBranchNames)

        createMissingJobs(expectedJobs, currentTemplateDrivenJobNames, templateJobs)
        deleteDeprecatedJobs(currentTemplateDrivenJobNames - expectedJobs.jobName)
        if (startExpected) {
            for (ConcreteJob expectedJob : expectedJobs) {
                jenkinsApi.startJob(expectedJob)
            }
        }
    }

    public void createMissingJobs(List<ConcreteJob> expectedJobs, List<String> currentJobs, List<TemplateJob> templateJobs) {
        List<ConcreteJob> missingJobs = expectedJobs.findAll { !currentJobs.contains(it.jobName) }
        if (!missingJobs) {
            println "No missing jobs to create.."
            return
        }

        for (ConcreteJob missingJob in missingJobs) {
            println "Creating missing job: ${missingJob.jobName} from ${missingJob.templateJob.jobName}"
            jenkinsApi.cloneJobForBranch(this.nestedView, missingJob, templateJobs)

            if (startOnCreate && !startExpected) {
                jenkinsApi.startJob(missingJob)
            }
        }

    }

    public void deleteDeprecatedJobs(List<String> deprecatedJobNames) {
        if (!deprecatedJobNames) return
        deprecatedJobNames.each { String jobName ->

            def shortenedJobName = jobName.substring(templateJobPrefix.length() + 1)
            def safeBranchNameRegex = branchNameRegex.replaceAll("\\/", "_")
            final def branchNameRegexMatches = shortenedJobName.matches(safeBranchNameRegex)
            if (!noDelete && branchNameRegexMatches) {
                println "Deleting deprecated job: $jobName"
                jenkinsApi.deleteJob(jobName)
            } else if (!branchNameRegexMatches) {
                println "Will not delete job: $jobName because it dos not comply to the branchNameRegex $branchNameRegex"
            } else {
                println "Would have deleted: $jobName but noDelete is set"
            }
        }
    }

    public List<ConcreteJob> expectedJobs(List<TemplateJob> templateJobs, List<String> branchNames) {
        branchNames.collect { String branchName ->
            templateJobs.collect { TemplateJob templateJob -> templateJob.concreteJobForBranch(branchName) }
        }.flatten()
    }

    public List<String> templateDrivenJobNames(List<TemplateJob> templateJobs, List<String> allJobNames) {
        List<String> templateJobNames = templateJobs.jobName
        List<String> templateBaseJobNames = templateJobs.baseJobName

        // don't want actual template jobs, just the jobs that were created from the templates
        return (allJobNames - templateJobNames).findAll { String jobName ->
            templateBaseJobNames.find { String baseJobName -> jobName.startsWith(baseJobName) }
        }
    }

    List<TemplateJob> findRequiredTemplateJobs(List<String> allJobNames) {
        String regex = /^($templateJobPrefix-?[^-]*)-($templateBranchName)$/

        List<TemplateJob> templateJobs = allJobNames.findResults { String jobName ->
            TemplateJob templateJob = null
            jobName.find(regex) { full, baseJobName, branchName ->
                templateJob = new TemplateJob(jobName: full, baseJobName: baseJobName, templateBranchName: branchName)
            }

            return templateJob
        }

        assert templateJobs?.size() > 0, "Unable to find any jobs matching template regex: $regex\nYou need at least one job to match the templateJobPrefix and templateBranchName suffix arguments"

        return templateJobs
    }

    JenkinsApi initJenkinsApi() {
        if (!jenkinsApi) {
            assert jenkinsUrl != null
            if (dryRun) {
                println "DRY RUN! Not executing any POST commands to Jenkins, only GET commands"
                this.jenkinsApi = new JenkinsApiReadOnly(jenkinsServerUrl: jenkinsUrl)
            } else {
                this.jenkinsApi = new JenkinsApi(jenkinsServerUrl: jenkinsUrl)
            }

            if (jenkinsUser || jenkinsPassword) this.jenkinsApi.addBasicAuth(jenkinsUser, jenkinsPassword)
        }

        return this.jenkinsApi
    }

    GitApi initGitApi() {
        if (!gitApi) {
            assert gitUrl != null
            this.gitApi = new GitApi(gitUrl: gitUrl)
            if (this.branchNameRegex) {
                this.gitApi.branchNameFilter = ~this.branchNameRegex
            }
        }

        return this.gitApi
    }
}
