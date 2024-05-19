/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vertexai.gemini.function;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiModelCall;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.model.function.FunctionCallbackWrapper.Builder.SchemaType;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
public class VertexAiGeminiModelCallFunctionCallingIT {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private VertexAiGeminiModelCall vertexGeminiClient;

	@AfterEach
	public void afterEach() {
		try {
			Thread.sleep(3000);
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Test
	@Disabled("Google Vertex AI degraded support for parallel function calls")
	public void functionCallExplicitOpenApiSchema() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, in Paris and in Tokyo, Japan?"
						+ " Use Celsius units. Answer for all requested locations.");
		// " Use Celsius units. Use Multi-turn function calling. Provide answer for all
		// requested locations.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		String openApiSchema = """
				{
					"type": "OBJECT",
					"properties": {
					  "location": {
						"type": "STRING",
						"description": "The city and state e.g. San Francisco, CA"
					  },
					  "unit" : {
						"type" : "STRING",
						"enum" : [ "C", "F" ],
						"description" : "Temperature unit"
					  }
					},
					"required": ["location", "unit"]
				  }
					""";

		var promptOptions = VertexAiGeminiChatOptions.builder()
			.withModel(VertexAiGeminiModelCall.ChatModel.GEMINI_PRO)
			// .withModel(VertexAiGeminiModelCall.ChatModel.GEMINI_PRO_1_5_PRO)
			.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
				.withName("get_current_weather")
				.withDescription("Get the current weather in a given location")
				.withInputTypeSchema(openApiSchema)
				.build()))
			.build();

		ChatResponse response = vertexGeminiClient.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		// System.out.println(response.getResult().getOutput().getContent());
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("30.0", "30");
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("10.0", "10");
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("15.0", "15");

	}

	@Test
	public void functionCallTestInferredOpenApiSchema() {

		UserMessage userMessage = new UserMessage("What's the weather like in Paris? Use Celsius units.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = VertexAiGeminiChatOptions.builder()
			// .withModel(VertexAiGeminiModelCall.ChatModel.GEMINI_PRO_1_5_PRO)
			.withModel(VertexAiGeminiModelCall.ChatModel.GEMINI_PRO.getValue())
			.withFunctionCallbacks(List.of(
					FunctionCallbackWrapper.builder(new MockWeatherService())
						.withSchemaType(SchemaType.OPEN_API_SCHEMA)
						.withName("get_current_weather")
						.withDescription("Get the current weather in a given location.")
						.build(),
					FunctionCallbackWrapper.builder(new PaymentStatus())
						.withSchemaType(SchemaType.OPEN_API_SCHEMA)
						.withName("get_payment_status")
						.withDescription(
								"Retrieves the payment status for transaction. For example what is the payment status for transaction 700?")
						.build()))
			.build();

		ChatResponse response = vertexGeminiClient.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("15.0", "15");

		ChatResponse response2 = vertexGeminiClient
			.call(new Prompt("What is the payment status for transaction 696?", promptOptions));

		logger.info("Response: {}", response2);

		assertThat(response2.getResult().getOutput().getContent()).containsIgnoringCase("transaction 696 is PAYED");

	}

	@Test
	public void functionCallTestInferredOpenApiSchemaStream() {

		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco in Celsius units?");
		// UserMessage userMessage = new UserMessage(
		// "What's the weather like in San Francisco, in Paris and in Tokyo, Japan? Use
		// Multi-turn function calling. Provide answer for all requested locations.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = VertexAiGeminiChatOptions.builder()
			.withModel(VertexAiGeminiModelCall.ChatModel.GEMINI_PRO)
			.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
				.withSchemaType(SchemaType.OPEN_API_SCHEMA)
				.withName("getCurrentWeather")
				.withDescription("Get the current weather in a given location")
				.build()))
			.build();

		Flux<ChatResponse> response = vertexGeminiClient.stream(new Prompt(messages, promptOptions));

		String responseString = response.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		logger.info("Response: {}", responseString);

		// assertThat(responseString).containsAnyOf("15.0", "15");
		assertThat(responseString).containsAnyOf("30.0", "30");
		// assertThat(responseString).containsAnyOf("10.0", "10");

	}

	public record PaymentInfoRequest(String id) {
	}

	public record TransactionStatus(String status) {
	}

	public static class PaymentStatus implements Function<PaymentInfoRequest, TransactionStatus> {

		@Override
		public TransactionStatus apply(PaymentInfoRequest paymentInfoRequest) {
			return new TransactionStatus("Transaction " + paymentInfoRequest.id() + " is PAYED");
		}

	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public VertexAI vertexAiApi() {
			String projectId = System.getenv("VERTEX_AI_GEMINI_PROJECT_ID");
			String location = System.getenv("VERTEX_AI_GEMINI_LOCATION");
			return new VertexAI.Builder().setLocation(location)
				.setProjectId(projectId)
				.setTransport(Transport.REST)
				.build();
		}

		@Bean
		public VertexAiGeminiModelCall vertexAiEmbedding(VertexAI vertexAi) {
			return new VertexAiGeminiModelCall(vertexAi,
					VertexAiGeminiChatOptions.builder()
						.withModel(VertexAiGeminiModelCall.ChatModel.GEMINI_PRO)
						.withTemperature(0.9f)
						.build());
		}

	}

}