package de.tum.in.www1.artemis.service.distributed.messages;

public class UpdateQuizMessage extends SynchronizationMessage {

    private Long quizId;

    public UpdateQuizMessage(String sendingServer, Long quizId) {
        super(sendingServer);
        this.quizId = quizId;
    }

    public UpdateQuizMessage(Long quizId) {
        super(null);
        this.quizId = quizId;
    }

    public Long getQuizId() {
        return quizId;
    }

    public void setQuizId(Long quizId) {
        this.quizId = quizId;
    }

    @Override
    public String toString() {
        return "UpdateQuizMessage{" + "quizId=" + quizId + '}';
    }
}
