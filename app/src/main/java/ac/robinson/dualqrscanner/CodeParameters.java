/*
 * Copyright (C) 2014 Simon Robinson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ac.robinson.dualqrscanner;

import android.graphics.PointF;

@SuppressWarnings("WeakerAccess")
public class CodeParameters {
	@SuppressWarnings("unused")
	public final PointF[] mIdPoints;
	@SuppressWarnings("unused")
	public final PointF[] mAlignmentPoints;
	@SuppressWarnings("unused")
	public final float mPointSpacing;

	public CodeParameters(PointF[] idPoints, PointF[] alignmentPoints, float pointSpacing) {
		// clone so we're not reliant on the originals
		int length = idPoints.length;
		mIdPoints = new PointF[length];
		for (int i = 0; i < length; i++) {
			PointF p = idPoints[i];
			mIdPoints[i] = new PointF(p.x, p.y);
		}

		length = alignmentPoints.length;
		mAlignmentPoints = new PointF[length];
		for (int i = 0; i < length; i++) {
			PointF p = alignmentPoints[i];
			mAlignmentPoints[i] = new PointF(p.x, p.y);
		}

		mPointSpacing = pointSpacing;
	}
}
