package io.github.pietrokucharski.kibble_api;

import org.springframework.boot.SpringApplication;

public class TestKibbleApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(KibbleApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
