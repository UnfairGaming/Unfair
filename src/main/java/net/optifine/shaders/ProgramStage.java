package net.optifine.shaders;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public enum ProgramStage
{
    NONE(""),
    SHADOW("shadow"),
    GBUFFERS("gbuffers"),
    DEFERRED("deferred"),
    COMPOSITE("composite");

    private String name;

    ProgramStage(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return this.name;
    }
}
