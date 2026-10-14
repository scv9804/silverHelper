package com.silverhelper.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class ChatDtos {
    private ChatDtos() { }
    public record Question(@NotBlank @Size(max=1000) String question) { }
    public record Basis(OffsetDateTime lastCollectedAt, OffsetDateTime sourceModifiedAt, Short baseYear,
            boolean updatePending) { }
    public record Source(long benefitId, String name, String sourceUrl, Basis basis) { }
    public record Answer(String answer, List<Source> sources, boolean mock) { }
}
