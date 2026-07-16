package com.ceilbhin.sigil

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
@EnableConfigurationProperties
class SigilApplication

fun main(args: Array<String>) {
    runApplication<SigilApplication>(*args)
}
