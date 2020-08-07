package de.tum.in.www1.artemis.service.scheduled;

import static java.time.Instant.now;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.EmbeddingTrainingMaterialService;

@Service
@Profile("automaticText")
public class EmbeddingTrainingMaterialScheduleService {

    private final Logger log = LoggerFactory.getLogger(EmbeddingTrainingMaterialScheduleService.class);

    private final Map<Long, ScheduledFuture> scheduledClusteringTasks = new HashMap<>();

    private final EmbeddingTrainingMaterialService embeddingTrainingMaterialService;

    private final TaskScheduler scheduler;

    public EmbeddingTrainingMaterialScheduleService(EmbeddingTrainingMaterialService embeddingTrainingMaterialService, @Qualifier("taskScheduler") TaskScheduler scheduler) {
        this.embeddingTrainingMaterialService = embeddingTrainingMaterialService;
        this.scheduler = scheduler;
    }

    /**
     * starts an upload process of material to Athene asynchronously
     * @param attachment - the attachment to be uploaded
     */
    public void scheduleMaterialUploadForNow(Attachment attachment) {
        // TODO: sanity checks.
        scheduler.schedule(trainingMaterialUploadRunnable(attachment), now());
        log.debug("Scheduled upload for the attachment \"" + attachment.getName() + "\"");
    }

    @NotNull
    private Runnable trainingMaterialUploadRunnable(Attachment attachment) {
        return () -> {
            SecurityUtils.setAuthorizationObject();
            try {
                embeddingTrainingMaterialService.uploadAttachment(attachment);
            }
            catch (NetworkingError networkingError) {
                log.error(networkingError.getMessage(), networkingError);
            }
        };
    }

    /**
     * cancel attachment upload to Athene
     * @param attachment - the attachment for which upload is to be canceled
     */
    public void cancelScheduledupload(Attachment attachment) {
        final ScheduledFuture future = scheduledClusteringTasks.get(attachment.getId());
        if (future != null) {
            future.cancel(false);
            scheduledClusteringTasks.remove(attachment.getId());
        }
    }

}
