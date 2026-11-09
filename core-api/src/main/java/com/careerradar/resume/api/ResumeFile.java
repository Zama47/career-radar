package com.careerradar.resume.api;

import org.springframework.core.io.Resource;

public record ResumeFile(Resource resource, String downloadName) {
}
