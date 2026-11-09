package com.careerradar.ai;

record CoverLetterPayload(String subject, String text) {

    CoverLetterPayload {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("cover letter text is empty");
        }
        if (subject == null || subject.isBlank()) {
            subject = "Отклик на вакансию";
        }
    }
}
