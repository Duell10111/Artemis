package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.service.FileService;

/**
 * NOTE: The file management is necessary to differentiate between temporary and used files and to delete used files when the corresponding submission is deleted. The workflow is
 * as follows: 1. user uploads a file -> temporary file is created and at this point we don't know if submission is already created, when submission is created file is moved to permanent location =>
 * This happens in @PreUpdate 2. When submission is deleted, the file in the permanent location is deleted => This happens in @PostRemove
 */
@Entity
@DiscriminatorValue(value = "F")
public class FileUploadSubmission extends Submission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Transient
    private FileService fileService = new FileService();

    @Column(name = "file_path")
    private String filePath;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

    @PreUpdate
    public void onUpdate() {
        // move file and delete old file if necessary
        filePath = fileService.manageFilesForUpdatedFilePath(null, filePath,
                Constants.FILE_UPLOAD_EXERCISES_FILEPATH + getParticipation().getExercise().getId() + '/' + getId() + '/', getId(), true);
    }

    @PostRemove
    public void onDelete() {
        // delete old file if necessary
        fileService.manageFilesForUpdatedFilePath(null, filePath, Constants.FILE_UPLOAD_EXERCISES_FILEPATH + getParticipation().getExercise().getId() + '/' + getId() + '/',
                getId(), true);
    }

    public String getFilePath() {
        return filePath;
    }

    public FileUploadSubmission filePath(String filePath) {
        this.filePath = filePath;
        return this;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FileUploadSubmission fileUploadSubmission = (FileUploadSubmission) o;
        if (fileUploadSubmission.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), fileUploadSubmission.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "FileUploadSubmission{" + "id=" + getId() + ", filePath='" + getFilePath() + "'" + "}";
    }
}
