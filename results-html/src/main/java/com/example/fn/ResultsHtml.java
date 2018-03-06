package com.example.fn;

import com.example.fn.messages.BallotData;
import com.example.fn.messages.VoteTally;
import com.fnproject.fn.api.Headers;
import com.fnproject.fn.api.OutputEvent;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.*;
import java.util.Optional;

public class ResultsHtml {

    public OutputEvent generateResults(VoteTally input) throws IOException, TemplateException {
        return new FreemarkerOutputEvent(input);
    }

    private class FreemarkerOutputEvent implements OutputEvent {
        private final VoteTally input;
        private final Template template;

        private Configuration templates() {
            Configuration templateConfig = new Configuration(Configuration.VERSION_2_3_27);
            templateConfig.setDefaultEncoding("UTF-8");
            templateConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            templateConfig.setLogTemplateExceptions(false);
            templateConfig.setWrapUncheckedExceptions(true);
            templateConfig.setClassForTemplateLoading(ResultsHtml.class, "/");
            return templateConfig;
        }

        public FreemarkerOutputEvent(VoteTally input) throws IOException {
            this.input = input;
            this.template = templates().getTemplate("results.html");
        }

        @Override
        public int getStatusCode() {
            return 200;
        }

        @Override
        public Optional<String> getContentType() {
            return Optional.of("text/html");
        }

        @Override
        public Headers getHeaders() {
            return Headers.emptyHeaders();
        }

        @Override
        public void writeToOutput(OutputStream outputStream) {
            try {
                template.process(input.toTemplateData(), new OutputStreamWriter(outputStream));
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }
}
