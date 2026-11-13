package com.vibecircle.moderation;

/**
 * DISMISS             : report is unfounded, nothing changes
 * REMOVE_CONTENT      : delete the reported post / comment / message
 * SUSPEND_USER        : suspend the owner of the reported item (or the reported user)
 * REMOVE_AND_SUSPEND  : both
 */
public enum ModerationAction { DISMISS, REMOVE_CONTENT, SUSPEND_USER, REMOVE_AND_SUSPEND }
