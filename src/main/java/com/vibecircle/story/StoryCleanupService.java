package com.vibecircle.story;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.vibecircle.media.MediaStorage;

@Service
public class StoryCleanupService {

    static final int BATCH_SIZE = 200;

    private final StoryRepository repo;
    private final MediaStorage storage;
    private final StoryProperties props;

    public StoryCleanupService(StoryRepository repo, MediaStorage storage, StoryProperties props) {
        this.repo = repo;
        this.storage = storage;
        this.props = props;
    }

    /**
     * Removes one batch of stories that expired (or were deleted) longer ago than the retention period.
     * Rows go first; files are deleted only after the commit, so a crash can leave an orphan file
     * but never a row that points at a missing file.
     */
    @Transactional
    public int removeBatch() {
        double grace = props.retentionAfterExpiry().toMillis() / 1000.0;
        List<StoryRepository.RemovableStory> batch = repo.lockRemovable(grace, BATCH_SIZE);
        if (batch.isEmpty()) {
            return 0;
        }
        repo.deleteAll(batch.stream().map(StoryRepository.RemovableStory::id).toList());
        List<String> keys = batch.stream().map(StoryRepository.RemovableStory::storageKey)
                .filter(k -> k != null).toList();
        if (!keys.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    keys.forEach(storage::delete);
                }
            });
        }
        return batch.size();
    }
}
