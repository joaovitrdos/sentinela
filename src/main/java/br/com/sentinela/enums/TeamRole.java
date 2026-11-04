package br.com.sentinela.enums;

public enum TeamRole {
    VIEWER, EDITOR, ADMIN;

    public boolean canEdit() { return this == EDITOR || this == ADMIN; }
    public boolean canDelete() { return this == ADMIN; }
}
