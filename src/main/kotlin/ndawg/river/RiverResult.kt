package ndawg.river

/**
 * A result that encapsulates data from a [RiverInvocation]. An instance of this class
 * is generated for every submission to the River.
 */
data class RiverResult<T : Any>(
	/** The event that this result corresponds to. */
	val event: T,
	/** The event invocation that this results corresponds to. */
	val invocation: RiverInvocation<T>,
	/** The listeners that received this result (before it was discarded, if it was) */
	val received: List<RiverListener<T>>,
	/** The discard exception received, if the event was discarded. */
	val discard: DiscardException?,
	/** The data yielded from the result. */
	val data: RiverData
) {
	
	val discarded: Boolean = discard != null
	
	/**
	 * Executes the given block if the event was discarded. This makes control flow easy,
	 * as statements can be checked to see if a chain should stop. For example:
	 *
	 * ```
	 * river.submit(event1).ifDiscarded { return }
	 * river.submit(event2)
	 * ```
	 */
	inline fun ifDiscarded(block: (DiscardException) -> Unit) {
		if (this.discarded) block(discard!!)
	}
	
}
