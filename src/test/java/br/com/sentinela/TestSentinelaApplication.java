package br.com.sentinela;

import org.springframework.boot.SpringApplication;

public class TestSentinelaApplication {

    public static void main(String[] args) {
        SpringApplication.from(SentinelaApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
