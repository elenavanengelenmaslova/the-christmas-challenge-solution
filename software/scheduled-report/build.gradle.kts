
dependencies {
    implementation("com.amazonaws:aws-lambda-java-core:1.2.2")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.0")
    implementation("software.amazon.awssdk:dynamodb-enhanced:2.18.41")
    implementation("org.slf4j:slf4j-api:2.0.5")
    implementation("org.slf4j:slf4j-simple:2.0.5")
}

configurations {
    runtimeClasspath {
        exclude("org.apache.httpcomponents")
        exclude("org.jetbrains")
    }
}

tasks.register<Zip>("packageDistribution") {
    from(tasks.compileKotlin)
    from(tasks.compileJava)
    from(tasks.processResources)
    into("lib") {
        from(configurations.runtimeClasspath)
    }
    archiveFileName.set("scheduled-report.zip")
    destinationDirectory.set(file("${project.rootDir}/build/dist"))
    dependsOn(":scheduled-report:build")
}
