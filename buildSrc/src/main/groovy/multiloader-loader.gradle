plugins {
    id 'multiloader-common'
}

configurations {
    commonJava{
        canBeResolved = true
    }
    commonResources{
        canBeResolved = true
    }
}

dependencies {
    compileOnly(project(':common')) {
        capabilities {
            requireCapability "$group:$mod_id"
        }
        def loaderAttribute = Attribute.of('io.github.mcgradleconventions.loader', String)
        attributes {
            attribute(loaderAttribute, 'common')
        }
    }
    commonJava project(path: ':common', configuration: 'commonJava')
    commonResources project(path: ':common', configuration: 'commonResources')
}

tasks.named('compileJava', JavaCompile) {
    dependsOn(configurations.commonJava)
    source(configurations.commonJava)
}

processResources {
    dependsOn(configurations.commonResources)
    from(configurations.commonResources)
}

tasks.named('javadoc', Javadoc).configure {
    dependsOn(configurations.commonJava)
    source(configurations.commonJava)
}

tasks.named('sourcesJar', Jar) {
    dependsOn(configurations.commonJava)
    from(configurations.commonJava)
    dependsOn(configurations.commonResources)
    from(configurations.commonResources)
}
