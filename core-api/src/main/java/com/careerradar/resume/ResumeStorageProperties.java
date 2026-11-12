package com.careerradar.resume;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local PDF storage for the MVP. Files are addressed by their generated name, never by a
 * user-supplied path.
 */
@ConfigurationProperties(prefix = "career-radar.resume-storage")
public class ResumeStorageProperties {

    private String dir = "./data/resumes";
    private long maxBytes = 10L * 1024 * 1024;

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }
}
