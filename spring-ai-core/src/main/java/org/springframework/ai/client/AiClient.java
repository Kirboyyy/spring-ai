/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.client;

import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.messages.UserMessage;

@FunctionalInterface
public interface AiClient {

	default String generate(String message) {
		Prompt prompt = new Prompt(new UserMessage(message));
		return generate(prompt).getGenerations().get(0).getText();
	}

	AiResponse generate(Prompt prompt);

}