<script>
export default {
  onLaunch: function () {
    console.log('App Launch')
  },
  onShow: function () {},
  onHide: function () {}
}
</script>

<style>
/* ===== 设计令牌：清新自然绿 ===== */
page {
  --c-primary: #3DBA6F;
  --c-primary-d: #2E9E5B;
  --c-primary-l: #6FD299;
  --c-accent: #F5A623;
  --c-bg: #F1FAF4;
  --c-card: #FFFFFF;
  --c-text: #1F2A24;
  --c-muted: #8A9A90;
  --c-line: #E6EFE9;
  background-color: #F1FAF4;
  font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif;
  color: #1F2A24;
}

.page-body { padding: 28rpx 32rpx 40rpx; }

.card {
  background: #fff;
  border-radius: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(45, 140, 85, 0.08);
  padding: 32rpx;
}

.btn {
  display: flex; align-items: center; justify-content: center;
  height: 92rpx; border-radius: 26rpx;
  background: var(--c-primary); color: #fff; font-size: 32rpx; font-weight: 600;
}
.btn-hover { background: var(--c-primary-d); }
.btn.disabled { background: #C7D8CE; color: #fff; }
.btn-ghost { background: #EAF6EF; color: var(--c-primary-d); }

.field-label { font-size: 26rpx; color: var(--c-muted); margin: 28rpx 4rpx 16rpx; }
</style>
