package dev.higor.trackflow.api.project.application;

/** The only object that ever carries the plain token: returned once, to the owner who created it. */
public record CreatedInvite(InviteView invite, String token) {}
