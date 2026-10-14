package com.silverhelper.pipeline.service;

import java.util.List;

/** 원천과 기존 저장값에서 사용하는 시도 명칭을 현재 행정구역명으로 맞춘다. */
public final class RegionNames {
    public static final String INTEGRATED = "전남광주통합특별시";
    public static final String FORMER_GWANGJU = "광주광역시";
    public static final String FORMER_JEONNAM = "전라남도";

    private RegionNames() {}

    public static String normalize(String name) {
        return FORMER_GWANGJU.equals(name) || FORMER_JEONNAM.equals(name) ? INTEGRATED : name;
    }

    public static List<String> storedNames(String name) {
        return INTEGRATED.equals(normalize(name))
                ? List.of(INTEGRATED, FORMER_GWANGJU, FORMER_JEONNAM)
                : List.of(name);
    }
}
