"use client";

import { Button, Modal } from 'antd';
import { CheckCircleOutlined, RedoOutlined } from '@ant-design/icons';
import { useCallback, useEffect, useRef, useState } from 'react';

interface SliderCaptchaProps {
  open: boolean;
  onSuccess: () => void;
  onCancel: () => void;
}

const WIDTH = 320;
const HEIGHT = 168;
const PIECE = 42;
const RADIUS = 9;
const TOLERANCE = 7;
const SUCCESS_FEEDBACK_DELAY = 120;

const drawPiecePath = (ctx: CanvasRenderingContext2D, x: number, y: number) => {
  ctx.beginPath();
  ctx.moveTo(x, y);
  ctx.lineTo(x + PIECE * 0.36, y);
  ctx.arc(x + PIECE * 0.5, y, RADIUS, Math.PI, 0, true);
  ctx.lineTo(x + PIECE, y);
  ctx.lineTo(x + PIECE, y + PIECE * 0.36);
  ctx.arc(x + PIECE, y + PIECE * 0.5, RADIUS, -Math.PI / 2, Math.PI / 2, false);
  ctx.lineTo(x + PIECE, y + PIECE);
  ctx.lineTo(x + PIECE * 0.64, y + PIECE);
  ctx.arc(x + PIECE * 0.5, y + PIECE, RADIUS, 0, Math.PI, false);
  ctx.lineTo(x, y + PIECE);
  ctx.closePath();
};

const randomBetween = (min: number, max: number) => Math.floor(min + Math.random() * (max - min));

const SliderCaptcha: React.FC<SliderCaptchaProps> = ({ open, onSuccess, onCancel }) => {
  const bgRef = useRef<HTMLCanvasElement>(null);
  const pieceRef = useRef<HTMLCanvasElement>(null);
  const trackRef = useRef<HTMLDivElement>(null);
  const handleRef = useRef<HTMLButtonElement>(null);
  const progressRef = useRef<HTMLDivElement>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [status, setStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const targetRef = useRef({ x: 180, y: 70 });
  const offsetRef = useRef(0);
  const pendingOffsetRef = useRef(0);
  const draggingRef = useRef(false);
  const animationFrameRef = useRef<number | null>(null);
  const feedbackTimerRef = useRef<number | null>(null);

  const draw = useCallback(() => {
    const bg = bgRef.current;
    const piece = pieceRef.current;
    if (!bg || !piece) return;

    if (feedbackTimerRef.current !== null) {
      window.clearTimeout(feedbackTimerRef.current);
      feedbackTimerRef.current = null;
    }

    const ctx = bg.getContext('2d');
    const pctx = piece.getContext('2d');
    if (!ctx || !pctx) return;

    const x = randomBetween(150, WIDTH - PIECE - 24);
    const y = randomBetween(34, HEIGHT - PIECE - 24);
    targetRef.current = { x, y };
    offsetRef.current = 0;
    pendingOffsetRef.current = 0;
    setStatus('idle');

    if (pieceRef.current) pieceRef.current.style.transform = 'translate3d(0, 0, 0)';
    if (handleRef.current) handleRef.current.style.left = '0px';
    if (progressRef.current) progressRef.current.style.width = '24px';

    ctx.clearRect(0, 0, WIDTH, HEIGHT);
    pctx.clearRect(0, 0, PIECE + RADIUS * 2, HEIGHT);

    const gradient = ctx.createLinearGradient(0, 0, WIDTH, HEIGHT);
    gradient.addColorStop(0, '#e8f8ff');
    gradient.addColorStop(0.5, '#f8fdff');
    gradient.addColorStop(1, '#edf4ff');
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, WIDTH, HEIGHT);

    for (let i = 0; i < 42; i += 1) {
      ctx.beginPath();
      ctx.fillStyle = `rgba(${randomBetween(80, 170)}, ${randomBetween(155, 220)}, ${randomBetween(210, 255)}, ${Math.random() * 0.22})`;
      ctx.arc(randomBetween(0, WIDTH), randomBetween(0, HEIGHT), randomBetween(3, 18), 0, Math.PI * 2);
      ctx.fill();
    }

    ctx.save();
    drawPiecePath(ctx, x, y);
    ctx.fillStyle = 'rgba(255,255,255,0.62)';
    ctx.fill();
    ctx.lineWidth = 2;
    ctx.strokeStyle = 'rgba(22,119,255,0.36)';
    ctx.stroke();
    ctx.restore();

    pctx.save();
    drawPiecePath(pctx, RADIUS, y);
    pctx.clip();
    pctx.drawImage(bg, x, 0, PIECE + RADIUS * 2, HEIGHT, RADIUS, 0, PIECE + RADIUS * 2, HEIGHT);
    pctx.restore();
    pctx.save();
    drawPiecePath(pctx, RADIUS, y);
    pctx.lineWidth = 2;
    pctx.strokeStyle = '#1677ff';
    pctx.shadowColor = 'rgba(22,119,255,0.28)';
    pctx.shadowBlur = 8;
    pctx.stroke();
    pctx.restore();
  }, []);

  useEffect(() => {
    if (!open) return;
    const timer = window.setTimeout(draw, 0);
    return () => {
      window.clearTimeout(timer);
      if (animationFrameRef.current !== null) {
        window.cancelAnimationFrame(animationFrameRef.current);
        animationFrameRef.current = null;
      }
      if (feedbackTimerRef.current !== null) {
        window.clearTimeout(feedbackTimerRef.current);
        feedbackTimerRef.current = null;
      }
      draggingRef.current = false;
      setIsDragging(false);
    };
  }, [draw, open]);

  const renderOffset = (nextOffset: number) => {
    offsetRef.current = nextOffset;
    pendingOffsetRef.current = nextOffset;
    // Keep the hot pointer-move path out of React. Updating these layers directly
    // prevents a queued render or CSS transition from making the slider lag behind
    // a fast pointer.
    if (pieceRef.current) pieceRef.current.style.transform = `translate3d(${nextOffset}px, 0, 0)`;
    if (handleRef.current) handleRef.current.style.left = `${nextOffset}px`;
    if (progressRef.current) progressRef.current.style.width = `${nextOffset + 24}px`;
  };

  const updateOffset = (clientX: number) => {
    if (!draggingRef.current || status === 'success' || !trackRef.current) return;
    const rect = trackRef.current.getBoundingClientRect();
    const max = Math.max(0, rect.width - 44);
    const nextOffset = Math.min(max, Math.max(0, clientX - rect.left - 22));
    pendingOffsetRef.current = nextOffset;
    if (animationFrameRef.current !== null) return;
    animationFrameRef.current = window.requestAnimationFrame(() => {
      animationFrameRef.current = null;
      renderOffset(pendingOffsetRef.current);
    });
  };

  const startDragging = (event: React.PointerEvent<HTMLButtonElement>) => {
    if (status === 'success') return;
    event.preventDefault();
    event.stopPropagation();
    draggingRef.current = true;
    setIsDragging(true);
    trackRef.current?.setPointerCapture(event.pointerId);
  };

  const release = (event?: React.PointerEvent<HTMLDivElement>) => {
    if (!draggingRef.current) return;
    event?.preventDefault();
    if (event && trackRef.current?.hasPointerCapture(event.pointerId)) {
      trackRef.current.releasePointerCapture(event.pointerId);
    }
    if (animationFrameRef.current !== null) {
      window.cancelAnimationFrame(animationFrameRef.current);
      animationFrameRef.current = null;
    }
    renderOffset(pendingOffsetRef.current);
    draggingRef.current = false;
    setIsDragging(false);
    const expectedOffset = targetRef.current.x - RADIUS;
    if (Math.abs(offsetRef.current - expectedOffset) <= TOLERANCE) {
      setStatus('success');
      feedbackTimerRef.current = window.setTimeout(onSuccess, SUCCESS_FEEDBACK_DELAY);
    } else {
      setStatus('error');
      window.setTimeout(() => {
        renderOffset(0);
        setStatus('idle');
      }, 360);
    }
  };

  return (
    <Modal open={open} footer={null} onCancel={onCancel} title="安全验证" width={380} centered destroyOnClose>
      <div className="puzzle-captcha">
        <div className="puzzle-board">
          <canvas ref={bgRef} width={WIDTH} height={HEIGHT} />
          <canvas
            ref={pieceRef}
            width={PIECE + RADIUS * 2}
            height={HEIGHT}
            className="puzzle-piece"
          />
        </div>
        <div
          ref={trackRef}
          className={`puzzle-track is-${status}${isDragging ? ' is-dragging' : ''}`}
          onPointerMove={(event) => updateOffset(event.clientX)}
          onPointerUp={release}
          onPointerCancel={release}
        >
          <span>{status === 'success' ? '验证通过' : status === 'error' ? '位置不正确，请重试' : '拖动滑块完成拼图'}</span>
          <div ref={progressRef} className="puzzle-progress" />
          <button
            type="button"
            ref={handleRef}
            className="puzzle-handle"
            onPointerDown={startDragging}
          >
            {status === 'success' ? <CheckCircleOutlined /> : '»'}
          </button>
        </div>
        <Button type="link" icon={<RedoOutlined />} onClick={draw}>换一张</Button>
      </div>
    </Modal>
  );
};

export default SliderCaptcha;
