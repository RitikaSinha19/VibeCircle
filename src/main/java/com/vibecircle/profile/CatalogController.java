package com.vibecircle.profile;

import com.vibecircle.city.CityRepository;
import com.vibecircle.interest.Interest;
import com.vibecircle.interest.InterestRepository;
import com.vibecircle.profile.dto.CityDto;
import com.vibecircle.profile.dto.InterestDto;
import com.vibecircle.profile.dto.InterestGroupDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only reference data used by the profile setup screens. */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CityRepository cities;
    private final InterestRepository interests;

    public CatalogController(CityRepository cities, InterestRepository interests) {
        this.cities = cities;
        this.interests = interests;
    }

    /** GET /api/cities?q=ban&limit=10  (empty q lists the first cities alphabetically) */
    @GetMapping("/cities")
    public List<CityDto> searchCities(@RequestParam(defaultValue = "") String q,
                                      @RequestParam(defaultValue = "20") int limit) {
        var page = PageRequest.of(0, Math.max(1, Math.min(limit, 50)), Sort.by("name"));
        var result = q.isBlank()
                ? cities.findAll(page).getContent()
                : cities.findByNameStartingWithIgnoreCase(q.trim(), page);
        return result.stream()
                .map(c -> new CityDto(c.getId(), c.getName(), c.getState(), c.getCountry()))
                .toList();
    }

    /** GET /api/interests -> grouped by category, ready for the picker UI */
    @GetMapping("/interests")
    public List<InterestGroupDto> allInterests() {
        Map<String, List<InterestDto>> grouped = new LinkedHashMap<>();
        for (Interest i : interests.findAllByOrderByCategoryAscNameAsc()) {
            grouped.computeIfAbsent(i.getCategory(), k -> new ArrayList<>())
                    .add(new InterestDto(i.getId(), i.getName(), i.getCategory()));
        }
        return grouped.entrySet().stream()
                .map(e -> new InterestGroupDto(e.getKey(), e.getValue()))
                .toList();
    }
}
