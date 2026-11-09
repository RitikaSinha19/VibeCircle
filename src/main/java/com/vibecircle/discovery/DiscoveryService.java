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

    private final DiscoveryRepository repository;

    public DiscoveryService(DiscoveryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DiscoveryPage discover(Long me, Long cityIdOverride, Long interestId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        var ctx = repository.findMyContext(me).orElse(new DiscoveryRepository.MyContext(null, 0));

        Long cityId = cityIdOverride != null ? cityIdOverride : ctx.cityId();
        if (cityId == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Choose your city in your profile to discover people");
        }
        if (interestId == null && ctx.interestCount() == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Select at least one interest to get recommendations");
        }

        // Fetch one extra row to learn whether another page exists, without a COUNT query.
        List<DiscoveredUser> rows = repository.discover(me, cityId, interestId, safeSize + 1, safePage * safeSize);
        boolean hasNext = rows.size() > safeSize;
        List<DiscoveredUser> items = hasNext ? rows.subList(0, safeSize) : rows;
        return new DiscoveryPage(List.copyOf(items), safePage, safeSize, hasNext);
    }

    @Transactional
    public void hide(Long me, Long target) {
        if (me.equals(target)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot hide yourself");
        }
        if (!repository.activeUserExists(target)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
        repository.hide(me, target);
    }

    @Transactional
    public void unhide(Long me, Long target) {
        repository.unhide(me, target);
    }
}
