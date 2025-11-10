package com.framstag.llmaj.templating;

import org.apache.commons.codec.CharEncoding;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import java.nio.file.Path;
import java.util.Set;

public class RawFileResolver extends FileTemplateResolver {

    public RawFileResolver(Path analysisDir) {
        setPrefix(analysisDir.toString() + "/");
        setResolvablePatterns(Set.of("facts/*"));
        setCacheable(false);
        setSuffix(".md");
        setTemplateMode(TemplateMode.TEXT);
        setForceTemplateMode(false);
        setCharacterEncoding(CharEncoding.UTF_8);
    }
}
