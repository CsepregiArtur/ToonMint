package com.toonmint.service;

import com.toonmint.config.AppProperties;
import com.toonmint.domain.Job;
import com.toonmint.web.dto.JobResponse;
import org.springframework.stereotype.Component;

@Component
public class JobMapper {

    private final AppProperties properties;

    /**
     * Injectează configurația pentru etichetele stilurilor.
     */
    public JobMapper(AppProperties properties) {
        this.properties = properties;
    }

    /**
     * Mapează un job la DTO-ul public.
     */
    public JobResponse toResponse(Job job) {
        return JobResponse.from(job, styleLabel(job.getStyleId()));
    }

    /**
     * Returnează eticheta unui stil sau id-ul dacă stilul nu mai există.
     */
    public String styleLabel(String styleId) {
        AppProperties.Jobs.Style style = properties.jobs().styles().get(styleId);
        return style == null ? styleId : style.label();
    }
}
