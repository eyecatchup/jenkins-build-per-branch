See the [documentation page](http://entagen.github.com/jenkins-build-per-branch/) for project details and usage.

This fork is based on Gradle 2.x instead of the original which was on 1.x.

Also added a fix where jenkins builds of cloned projects are not polling for new commits. Resaving the config after making the project seems to fix it. 
