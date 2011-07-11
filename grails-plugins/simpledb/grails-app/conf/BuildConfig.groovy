grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"

grails.project.dependency.resolution = {

    inherits "global"

    log "warn"

    repositories {
        grailsPlugins()
        grailsHome()
        grailsCentral()

        mavenLocal()
        mavenCentral()
        mavenRepo 'http://maven.springframework.org/milestone'
        mavenRepo 'http://maven.springframework.org/snapshot'
        mavenRepo 'http://repository.codehaus.org'
    }

    dependencies {

        def excludes = {
            excludes "slf4j-simple", "persistence-api", "commons-logging", "jcl-over-slf4j", "slf4j-api", "jta"
            excludes "spring-core", "spring-beans", "spring-aop", "spring-asm", "spring-webmvc", "spring-tx", "spring-context", "spring-web", "log4j", "slf4j-log4j12"
            excludes "stax-api" //this is needed for AWS api //http://grails.1312388.n4.nabble.com/How-can-I-solve-this-jar-conflict-issue-td3067041.html
        }
        runtime("org.grails:grails-datastore-gorm:1.0.0.M6", excludes)
        runtime("org.grails:grails-datastore-gorm-simpledb:1.0.0.BUILD-SNAPSHOT", excludes)
        runtime("org.springframework:spring-datastore-web:1.0.0.M6", excludes)
        runtime("stax:stax:1.2.0", excludes)

        test("org.grails:grails-datastore-gorm-test:1.0.0.M4", excludes)
    }
}