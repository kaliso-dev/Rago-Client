package net.minecraftforge.fml.common.eventhandler;

/** The base of every event, as mods compiled for Forge 1.8.9 see it. */
public class Event {
	public enum Result {
		DENY, DEFAULT, ALLOW
	}

	private boolean canceled;
	private Result result = Result.DEFAULT;

	public boolean isCancelable() {
		return true;
	}

	public boolean isCanceled() {
		return canceled;
	}

	public void setCanceled(boolean cancel) {
		canceled = cancel;
	}

	public boolean hasResult() {
		return false;
	}

	public Result getResult() {
		return result;
	}

	public void setResult(Result value) {
		result = value;
	}
}
