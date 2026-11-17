
package com.vibecircle.discovery;

import com.vibecircle.common.ApiException;
import com.vibecircle.discovery.dto.DiscoveredUser;
import com.vibecircle.discovery.dto.DiscoveryPage;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscoveryService {

    static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SEARCH_LENGTH = 100;

    private final DiscoveryRepository repository;

    public DiscoveryService(DiscoveryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DiscoveryPage discover(
            Long me,
            Long cityId,
            Long interestId,
            String query,
            int page,
            int size) {

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        String search = query == null ? null : query.trim();

        if (search != null && search.isBlank()) {
            search = null;
        }

        if (search != null && search.length() > MAX_SEARCH_LENGTH) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Search query cannot exceed 100 characters"
            );
        }

        // No city or interest is required for global discovery.
        List<DiscoveredUser> rows = repository.discover(
                me,
                cityId,
                interestId,
                search,
                safeSize + 1,
                safePage * safeSize
        );

        boolean hasNext = rows.size() > safeSize;

        List<DiscoveredUser> items = hasNext
                ? rows.subList(0, safeSize)
                : rows;

        return new DiscoveryPage(
                List.copyOf(items),
                safePage,
                safeSize,
                hasNext
        );
    }

    @Transactional
    public void hide(Long me, Long target) {

        if (me.equals(target)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "You cannot hide yourself"
            );
        }

        if (!repository.activeUserExists(target)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "User not found"
            );
        }

        repository.hide(me, target);
    }

    @Transactional
    public void unhide(Long me, Long target) {
        repository.unhide(me, target);
    }
}
