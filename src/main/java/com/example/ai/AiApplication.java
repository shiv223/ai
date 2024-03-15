package com.example.ai;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class AiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiApplication.class, args);
	}
	@Component
	static class CarinaAiClient{
		private final VectorStore vectorStore;
		private final ChatClient chatClient;
		CarinaAiClient(VectorStore vectorStore,ChatClient chatClient){
			this.vectorStore =vectorStore;
			this.chatClient = chatClient;
		}

		String chat(String message){
			var prompt = """
				Upon interacting with ResumeBot, users are prompted to upload their resume document
				 or input text directly into the chat interface. ResumeBot then swiftly analyzes the content, 
				 examining various factors such as formatting, structure, content relevance, and language usage.
					""";
					var listOfSimmilarDocs = this.vectorStore.similaritySearch(message);
					var docs = listOfSimmilarDocs.stream()
												.map(Document::getContent)
												.collect(Collectors.joining(System.lineSeparator()));

					var systemMessage = new SystemPromptTemplate(prompt)
													.createMessage(Map.of("documents",docs));
					var userMessage = new UserMessage(message);
					var promptList = new Prompt(List.of(systemMessage,userMessage));	
					var aiResponse= this.chatClient.call(promptList);
					return aiResponse.getResult().getOutput().getContent();							
				
		}
	}

	@Bean
	ApplicationRunner demo(
			VectorStore vectorStore,
			@Value("file:///Users/sibabrataacharya/Desktop/gugu.pdf") Resource pdf,
			JdbcTemplate template,
			ChatClient chatClient,
			CarinaAiClient carinaAiClient) {
		return args -> {
			pdfReaderSetup(vectorStore, pdf, template);

			System.out.println(carinaAiClient.chat("what is my name?"));



		};

	}

	private void pdfReaderSetup(VectorStore vectorStore, Resource pdf, JdbcTemplate template) {
		template.update("delete from vector_store");

		var config = PdfDocumentReaderConfig
				.builder()
				.withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder()
						.withNumberOfBottomTextLinesToDelete(3)
						.build())
				.build();

		var pdfReader = new PagePdfDocumentReader(pdf, config);
		var textSplitter = new TokenTextSplitter();

		var docs = textSplitter.apply(pdfReader.get());
		vectorStore.accept(docs);
	}

}
