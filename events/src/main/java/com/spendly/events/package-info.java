/**
 * Contract-only event schemas shared by every service.
 *
 * <p>
 * This module must stay free of Spring, Jakarta, Jackson and persistence types: a consumer should be able to depend on
 * the contracts without inheriting anyone's framework. The rule is enforced by {@code ContractPurityTest}, not just by
 * convention.
 */
@NullMarked
package com.spendly.events;

import org.jspecify.annotations.NullMarked;
