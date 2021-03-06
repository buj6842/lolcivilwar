package kr.co.mcedu.riot.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class ChampionData {
    private String id;
    private Long key;
    private String name;
}
