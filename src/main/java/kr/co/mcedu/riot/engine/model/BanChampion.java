package kr.co.mcedu.riot.engine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BanChampion {
    private Long championId;
    private String championName;
    private String championImage;
    private Long teamId;
    private Long pickTurn;
}
