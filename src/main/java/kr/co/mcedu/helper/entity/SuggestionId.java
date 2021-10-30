package kr.co.mcedu.helper.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

@Getter
@Setter
@Embeddable
public class SuggestionId implements Serializable {
    @Column(name = "ip")
    private String ip;
    @Column(name = "date")
    private String date;

    public String toString(){
        return "SuggestionId(ip :" +this.ip +", date:" + this.date +")";
    }

    public int hashCode(){
        int result = 0 ;
        result = this.ip != null ? this.ip.hashCode() : 0;
        result = 31 * result + (this.date != null ? this.date.hashCode() : 0 );
        return result;
    }

    public boolean equals(Object other){
        if(this == other){
            return  true;
        }else {
            return false;
        }
    }


}
