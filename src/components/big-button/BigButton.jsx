function BigButton({ children, className, onClickHandler }) {
  return (
    <button
      className={`big-button ${className ? className : ''}`}
      onClick={onClickHandler}
    >
      {children}
    </button>
  );
}

export default BigButton;
