interface Props {
  children: string;
  onClickHandler: () => void;
}

const CreateButton = ({ children, onClickHandler }: Props) => {
  return (
    <button className={'create-button'} onClick={onClickHandler}>
      {children}
    </button>
  );
};

export default CreateButton;
