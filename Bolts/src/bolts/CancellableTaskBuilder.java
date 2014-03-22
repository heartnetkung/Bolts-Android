package bolts;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import android.util.Log;

/**
 * Helper class to help implementing Bolt Framework easier with the additional
 * ability to cancel all running task.
 * 
 * @author heartnetkung
 * 
 * @param <Return>
 *            the return type of the task
 * @param <SubclassVariable>
 *            variable subclass could use to handle cancel, Void if not use.
 */
public abstract class CancellableTaskBuilder<Return, SubclassVariable> {

	private final String tag = getClass().getSimpleName();
	private Set<TaskController> running;

	public CancellableTaskBuilder() {
		running = Collections.synchronizedSet(new HashSet<TaskController>());
	}

	/**
	 * perform the task and get the Task object to be chainable
	 * 
	 * @return Task object
	 */
	protected final TaskController createTaskController(SubclassVariable variable) {
		Task<Return>.TaskCompletionSource t = Task.create();
		TaskController ans = new TaskController(t, variable);
		running.add(ans);
		return ans;
	}

	protected final Task<Return> doBGTask(final Callable<Return> callable) {
		Task<Return>.TaskCompletionSource t = Task.postInBackground(new Callable<Return>() {
			@Override
			public Return call() throws Exception {
				Log.d(tag, "task starts in bg");
				try {
					Return ans = callable.call();
					Log.d(tag, "task returning: " + ans);
					return ans;
				} catch (Exception e) {
					Log.e(tag, "task throwing: ", e);
					throw e;
				}
			}
		});
		TaskController ans = new TaskController(t, null);
		running.add(ans);
		return ans.getTask();
	}

	/**
	 * cancel all running task issued by this class
	 */
	@SuppressWarnings({ "unchecked" })
	public void cancelRunningTasks() {
		TaskController[] runningCache = running.toArray(new CancellableTaskBuilder.TaskController[0]);
		running = Collections.synchronizedSet(new HashSet<TaskController>());
		for (TaskController d : runningCache)
			try {
				SubclassVariable s = d.getVariable();
				if (s != null)
					onCancel(s);
				d.setCancelled();
			} catch (Exception e) {
				Log.e(tag, "error while canceling task: ", e);
			}
	}

	protected final Set<TaskController> getRunningTasks() {
		return Collections.unmodifiableSet(running);
	}

	/**
	 * Optional to implement. Do something when the cancel method is called on
	 * each individual task.
	 * 
	 * @param control
	 */
	protected void onCancel(SubclassVariable control) {
	}

	public final class TaskController {

		private final Task<Return>.TaskCompletionSource innerControl;
		private SubclassVariable variable;

		private TaskController(Task<Return>.TaskCompletionSource innerControl, SubclassVariable variable) {
			this.innerControl = innerControl;
			this.variable = variable;
			Log.d(tag, "new TaskController()");
		}

		public boolean setResult(Return r) {
			Log.d(tag, "TaskController.setResult(" + r + ")");
			running.remove(innerControl);
			return innerControl.trySetResult(r);
		}

		public boolean setError(Exception e) {
			Log.d(tag, "TaskController.setError(" + e + ")");
			running.remove(innerControl);
			return innerControl.trySetError(e);
		}

		public boolean setError(Exception e, String logMessage) {
			running.remove(innerControl);
			Log.e(tag, logMessage,e);
			return innerControl.trySetError(e);
		}

		public Task<Return> getTask() {
			return innerControl.getTask();
		}

		public SubclassVariable getVariable() {
			return variable;
		}

		public void setVariable(SubclassVariable variable) {
			this.variable = variable;
		}

		private boolean setCancelled() {
			return innerControl.trySetCancelled();
		}
	}
}
