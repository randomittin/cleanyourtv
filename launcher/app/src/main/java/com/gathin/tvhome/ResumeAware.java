package com.gathin.tvhome;

/** Home came to the foreground (end of onResume) or left it (onPause). */
interface ResumeAware {
    void onResume();

    void onPause();
}
