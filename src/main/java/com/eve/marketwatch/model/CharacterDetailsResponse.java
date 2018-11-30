package com.eve.marketwatch.model;

import com.google.gson.annotations.SerializedName;

public class CharacterDetailsResponse {

    @SerializedName("CharacterID")
    private Integer characterId;

    @SerializedName("CharacterName")
    private String characterName;

    public Integer getCharacterId() {
        return characterId;
    }

    public void setCharacterId(final Integer characterId) {
        this.characterId = characterId;
    }

    public String getCharacterName() {
        return characterName;
    }

    public void setCharacterName(final String characterName) {
        this.characterName = characterName;
    }
}
