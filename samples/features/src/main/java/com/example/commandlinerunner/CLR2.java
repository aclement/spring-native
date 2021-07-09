package com.example.commandlinerunner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("!apple")
@Component
public class CLR2 implements CommandLineRunner {

	@Override
	public void run(String... args) throws Exception {
		System.out.println("In a non-apple profile!");
	}
	
}