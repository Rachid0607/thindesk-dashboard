package br.umc.thindesk;

import br.umc.thindesk.config.MongoUriObrigatoriaInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ThindeskApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(ThindeskApplication.class);
		app.addInitializers(new MongoUriObrigatoriaInitializer());
		app.run(args);
	}

}
