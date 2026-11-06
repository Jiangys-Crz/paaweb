/** Motion constants and kinematics from ICPC Tools Resolver 2.7.1354. Positions are in rows. */
export type ResolverMovement = Readonly<{ acceleration: number; maxSpeed: number }>;

export const RESOLVER_ROW_MOVEMENT: ResolverMovement = { acceleration: 4, maxSpeed: 7 };
export const RESOLVER_SCROLL_MOVEMENT: ResolverMovement = { acceleration: 4, maxSpeed: 6 };
export const RESOLVER_FOCUS_MOVEMENT: ResolverMovement = { acceleration: 3, maxSpeed: 8 };

/** ResolutionUtil.DelayType ordinal order; the native values are seconds. */
export const RESOLVER_DELAY_MS = {
  SELECT_TEAM: 1300,
  SELECT_PROBLEM: 1000,
  SOLVED_MOVE: 2250,
  SOLVED_STAY: 1500,
  FAILED: 850,
  DESELECT: 250,
  SELECT_SUBMISSION: 450,
} as const;

/**
 * Port of the native Animator. Time is advanced in integral milliseconds, as in Java.
 * Each position has its own animator. Retargeting preserves the current movement,
 * including braking and reversing when the new target is behind the stopping point.
 */
export class ResolverAnimator {
  private acceleration: number;
  private initial: number;
  private target: number;
  private targetVelocity = 0;
  private accelerationTime = 0;
  private coastTime = 0;
  private timeScale = 1;
  private timeMs = 0;

  constructor(initial: number, private readonly movement: ResolverMovement) {
    this.initial = initial;
    this.target = initial;
    this.acceleration = movement.acceleration;
  }

  incrementTimeMs(deltaMs: number): void {
    this.timeMs += Math.trunc(deltaMs);
  }

  getMoveTime(): number {
    return this.accelerationTime * 2 + this.coastTime;
  }

  isMoving(): boolean {
    const duration = this.getMoveTime();
    return this.targetVelocity !== 0 || (duration > 0 && this.timeMs * this.timeScale < duration * 1000);
  }

  getValue(): number {
    let time = this.timeMs * this.timeScale / 1000;
    const first = this.accelerationTime;
    if (time < first) {
      return this.initial + this.acceleration * time * time / 2 + this.targetVelocity * time;
    }
    if (time < first + this.coastTime) {
      time -= first;
      return this.initial + this.acceleration * first * first / 2
        + time * this.movement.maxSpeed * Math.sign(this.target - this.initial)
        + this.targetVelocity * (first + time);
    }
    if (time < 2 * first + this.coastTime) {
      time = 2 * first + this.coastTime - time;
      return this.target - this.acceleration * time * time / 2
        + this.targetVelocity * (2 * first + this.coastTime - time);
    }
    return this.target + this.targetVelocity * time;
  }

  private getVelocity(): number {
    let time = this.timeMs * this.timeScale / 1000;
    if (time < this.accelerationTime) return this.acceleration * time * this.timeScale;
    if (time < this.accelerationTime + this.coastTime) {
      return this.movement.maxSpeed * Math.sign(this.target - this.initial) * this.timeScale;
    }
    if (time < this.getMoveTime()) {
      time = this.getMoveTime() - time;
      return this.acceleration * time * this.timeScale;
    }
    return 0;
  }

  resetToTarget(): void { this.reset(this.target); }

  reset(value: number, targetVelocity = 0): void {
    this.initial = value;
    this.target = value;
    this.accelerationTime = 0;
    this.coastTime = 0;
    this.targetVelocity = targetVelocity;
    this.timeScale = 1;
  }

  setTimeScale(scale: number): void {
    this.timeScale = Math.min(1, Math.max(0.1, scale));
  }

  getTarget(): number { return this.target; }

  setTarget(target: number, targetVelocity = 0): void {
    if (target === this.target && targetVelocity === this.targetVelocity) return;
    const velocity = this.getVelocity();
    this.initial = this.getValue();
    this.target = target;
    this.targetVelocity = targetVelocity;
    this.timeMs = 0;
    this.timeScale = 1;
    this.acceleration = this.movement.acceleration;
    if (this.target < this.initial) this.acceleration = -this.acceleration;

    if (velocity !== 0) {
      const stopTime = Math.abs(velocity / this.movement.acceleration);
      const brakingAcceleration = -Math.sign(velocity) * this.movement.acceleration;
      const stopPosition = this.initial + velocity * stopTime + brakingAcceleration * stopTime * stopTime / 2;
      if (stopPosition < Math.min(this.initial, this.target) || stopPosition > Math.max(this.initial, this.target)) {
        this.timeMs = -Math.trunc(stopTime * 1000);
        if (Math.sign(this.target - stopPosition) !== Math.sign(this.target - this.initial)) {
          this.acceleration = -this.acceleration;
        }
      } else {
        this.timeMs = Math.trunc(stopTime * 1000);
      }
      this.initial -= this.acceleration * stopTime * stopTime / 2 + this.timeMs * targetVelocity / 1000;
    }

    this.accelerationTime = Math.sqrt(Math.abs(this.target - this.initial) / this.movement.acceleration);
    const maximumAccelerationTime = this.movement.maxSpeed / this.movement.acceleration;
    if (this.accelerationTime > maximumAccelerationTime) {
      const accelerationDistance = this.movement.acceleration * maximumAccelerationTime * maximumAccelerationTime;
      this.coastTime = (Math.abs(this.target - this.initial) - accelerationDistance) / this.movement.maxSpeed;
      this.accelerationTime = maximumAccelerationTime;
    } else {
      this.coastTime = 0;
    }
  }
}
